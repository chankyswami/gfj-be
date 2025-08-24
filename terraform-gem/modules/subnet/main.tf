resource "aws_subnet" "this" {
  vpc_id                  = var.vpc_id
  cidr_block              = var.subnet_cidr
  availability_zone       = var.availability_zone
  map_public_ip_on_launch = false   # ensures EC2 gets public IP

  tags = {
    Name = "${var.vpc_name}-subnet"
  }
}

# Associate subnet with the public route table
resource "aws_route_table_association" "this" {
  subnet_id      = aws_subnet.this.id
  route_table_id = var.route_table_id
}
